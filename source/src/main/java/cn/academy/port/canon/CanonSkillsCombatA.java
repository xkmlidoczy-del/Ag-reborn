package cn.academy.port.canon;

import static cn.academy.port.core.SkillTuning.integer;
import static cn.academy.port.core.SkillTuning.number;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyConfig;
import cn.academy.port.SkillCatalog;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.ElectromasterPrototype;
import cn.academy.port.highpower.ElectromasterChargeDecoy;
import cn.academy.port.highpower.IronSandLance;
import cn.academy.port.highpower.MagneticArrowSeparation;
import cn.academy.port.highpower.MagneticDomainArrows;
import cn.academy.port.highpower.MagneticGroundStep;
import cn.academy.port.highpower.MultiAxisReflectionArrowBridge;
import cn.academy.port.highpower.ShortInterceptionArrowBridge;
import cn.academy.port.highpower.TrajectoryReturnArrowBridge;
import cn.academy.port.story.UrbanCalibrator;
import cn.academy.port.highpower.HighPowerGrowth;
import cn.academy.port.highpower.HighPowerResources;
import cn.academy.port.highpower.MagneticItemProvenance;
import cn.academy.port.highpower.CanonEmission;
import cn.academy.port.skill.ClassicRaytrace;
import cn.academy.port.story.UrbanStoryClues;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.math.BigDecimal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Server-authoritative first slice for canon abilities. Only the four implemented ids
 * below are admitted. Other planned ids must not be registered as playable placeholders.
 * Mental leases affect eligible hostile mobs, never players, boss entities or saved AI flags.
 */
@EventBusSubscriber(modid = "academy")
public final class CanonSkillsCombatA {
    private static final String MENTAL = "mental_out", AERO = "aero_hand", NITROGEN = "nitrogen_armor", DARK = "dark_matter";
    private static final String PROBE = "mental_probe", DISRUPT = "mental_disrupt",
            CROWD = "mental_out_crowd", JET = "aero_jet", LAUNCH = "aero_launch", BARRAGE = "aero_barrage",
            ARMOR = "nitrogen_armor", PUNCH = "nitrogen_punch", DOME = "nitrogen_dome",
            BLADE = "dark_blade", WINGS = "dark_wings", CONSTRUCT = "dark_construct";
    public static final Set<String> IMPLEMENTED = java.util.stream.Stream.concat(
            Set.of(PROBE, DISRUPT, CROWD, JET, LAUNCH, BARRAGE, ARMOR, PUNCH, DOME, BLADE, WINGS, CONSTRUCT).stream(),
            CanonExpansionA.skills().stream().map(SkillCatalog.Skill::id)).collect(java.util.stream.Collectors.toUnmodifiableSet());
    private static final String WINGS_MARK = "academy:dark_wings_owned_flight";
    private static final TagKey<EntityType<?>> CONTROLLABLE = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath("academy", "mental_controllable"));
    private static final Map<Mob, Lease> LEASES = new IdentityHashMap<>();
    private static final Map<ServerPlayer, Shell> SHELLS = new IdentityHashMap<>();
    private static final Map<ServerPlayer, Dome> DOMES = new IdentityHashMap<>();
    private static final Map<ServerPlayer, Wings> WINGS_ACTIVE = new IdentityHashMap<>();
    private static final Map<ServerPlayer, Constructs> CONSTRUCTS = new IdentityHashMap<>();
    private static final Set<ServerPlayer> PERFORMING = java.util.Collections.newSetFromMap(new IdentityHashMap<>());

    /** No NoAI or entity NBT writes: unloading, disconnect and restart cannot strand a mob. */
    private record Lease(ServerPlayer owner, ServerLevel level, Mob mob, Mob target, long expires) {}
    private record Shell(ServerPlayer owner, ServerLevel level, long expires) {}
    private record Dome(ServerPlayer owner, ServerLevel level, long expires) {}
    private record Wings(ServerPlayer owner, ServerLevel level, long expires) {}
    private static final class Constructs {
        final ServerPlayer owner; final ServerLevel level; final long expires;
        long lastTick = Long.MIN_VALUE, nextAttack;
        CanonEmission.ConstructCast permit;
        ConstructShot shot;
        Constructs(ServerPlayer owner, ServerLevel level, long expires) { this.owner = owner; this.level = level; this.expires = expires; }
    }
    private record ConstructShot(LivingEntity target, Vec3 origin, Vec3 endpoint, int shard, long tick) {}
    private CanonSkillsCombatA() {}

    public static boolean cast(ServerPlayer player, String skill) {
        if (CanonExpansionA.contains(skill)) return CanonExpansionCombatA.cast(player,skill);
        if (player == null || skill == null || !IMPLEMENTED.contains(skill) || !player.server.isSameThread()
                || !player.isAlive() || player.isRemoved() || player.isSpectator() || player.hasDisconnected()
                || AbilityConsumption.busy(player) || player.containerMenu != player.inventoryMenu
                || !finite(player.position()) || !finite(player.getEyePosition()) || !finite(player.getLookAngle())) return false;
        if (!PERFORMING.add(player)) return false;
        try {
            var state = AbilityStorage.get(player);
            String category = skill.equals(JET) || skill.equals(LAUNCH) || skill.equals(BARRAGE) ? AERO
                    : skill.equals(ARMOR) || skill.equals(PUNCH) || skill.equals(DOME) ? NITROGEN
                    : skill.equals(WINGS) || skill.equals(BLADE) || skill.equals(CONSTRUCT) ? DARK : MENTAL;
            if (!state.category.equals(category) || !state.canUse(skill) || !state.presets.currentContains(skill)
                    || !HighPowerResources.ready(player) || SkillCatalog.find(category, skill).isEmpty()
                    || !SkillCatalog.enabled(category, skill)
                    || SkillCatalog.find(category, skill).filter(s -> HighPowerResources.effectiveLevel(player) >= s.level()).isEmpty()) return false;
            return switch (skill) {
                case PROBE -> probe(player);
                case DISRUPT -> disrupt(player);
                case CROWD -> crowd(player);
                case JET -> jet(player);
                case LAUNCH -> launch(player);
                case BARRAGE -> barrage(player);
                case ARMOR -> armor(player);
                case PUNCH -> punch(player);
                case DOME -> dome(player);
                case BLADE -> blade(player);
                case WINGS -> wings(player);
                case CONSTRUCT -> construct(player);
                default -> false;
            };
        } finally { PERFORMING.remove(player); }
    }

    private static boolean probe(ServerPlayer player) {
        LivingEntity target = aimed(player, number(PROBE, "reach"));
        if (target == null) return false;
        boolean witness = UrbanStoryClues.canProbe(player, target);
        boolean monster = target instanceof Mob mob && eligible(player, mob, PROBE);
        if ((!witness && !monster) || !spend(player, PROBE, 1, 20)) return false;
        if (witness) {
            if (!UrbanStoryClues.probe(player, target)) return false; // A lost race never grants a fabricated clue.
        } else {
            if (!(target instanceof Mob mob) || !eligible(player, mob, PROBE)) return false;
            player.sendSystemMessage(Component.translatable(mob.getTarget() == null
                    ? "ac.canon.mental_probe.idle" : "ac.canon.mental_probe.alert"));
            CanonExpansionCombatA.reveal(player, mob, integer(PROBE, "reveal_ticks"), 1, integer(PROBE, "stagger_ticks"));
        }
        cue(player.serverLevel(), target.position().add(0, target.getBbHeight() * .7, 0), true);
        visual(player,PROBE,player.getEyePosition(),target.position().add(0,target.getBbHeight()*.5,0),CanonVfx.Phase.HIT,10);
        experience(player, PROBE, .02);
        return true;
    }

    private static boolean disrupt(ServerPlayer player) {
        int duration = integer(DISRUPT, "duration_ticks");
        LivingEntity hit = aimed(player, number(DISRUPT, "reach"));
        if (!(hit instanceof Mob mob) || !eligible(player, mob, DISRUPT) || LEASES.containsKey(mob) || CanonExpansionCombatA.mentalLeased(mob)) return false;
        long now = player.serverLevel().getGameTime();
        if (now < 0 || now > Long.MAX_VALUE - duration || !spend(player, DISRUPT, 2, 80)) return false;
        if (!validOwner(player) || !eligible(player, mob, DISRUPT) || LEASES.containsKey(mob) || CanonExpansionCombatA.mentalLeased(mob)) return false;
        LEASES.put(mob, new Lease(player, player.serverLevel(), mob, null, now + duration));
        mob.setTarget(null); mob.getNavigation().stop();
        if (mob.getTarget() != null) { LEASES.remove(mob); return false; }
        cue(player.serverLevel(), mob.position().add(0, mob.getBbHeight() * .7, 0), true);
        visual(player,DISRUPT,player.getEyePosition(),mob.position(),CanonVfx.Phase.ACTIVE,duration);
        experience(player, DISRUPT, .02);
        return true;
    }

    private static boolean crowd(ServerPlayer player) {
        int duration = integer(CROWD, "duration_ticks");
        double radius = number(CROWD, "radius");
        LivingEntity hit = aimed(player, number(CROWD, "reach"));
        if (!(hit instanceof Mob victim) || !eligible(player, victim, CROWD) || LEASES.containsKey(victim) || CanonExpansionCombatA.mentalLeased(victim)) return false;
        var nearby = new ArrayList<Mob>(player.serverLevel().getEntitiesOfClass(Mob.class,
                player.getBoundingBox().inflate(radius), candidate -> candidate != victim && eligible(player, candidate, CROWD)
                        && player.distanceToSqr(candidate) <= radius * radius
                        && !LEASES.containsKey(candidate) && !CanonExpansionCombatA.mentalLeased(candidate) && candidate.hasLineOfSight(victim)
                        && !candidate.isAlliedTo(victim) && !victim.isAlliedTo(candidate)));
        nearby.sort(Comparator.comparingDouble(player::distanceToSqr));
        if (nearby.isEmpty()) return false;
        List<Mob> selected = List.copyOf(nearby.subList(0, Math.min(integer(CROWD, "target_cap"), nearby.size())));
        long now = player.serverLevel().getGameTime();
        if (now < 0 || now > Long.MAX_VALUE - duration || !spend(player, CROWD, 5, 200)) return false;
        if (!validOwner(player) || !eligible(player, victim, CROWD) || CanonExpansionCombatA.mentalLeased(victim)) return false;
        int applied = 0;
        for (Mob mob : selected) {
            if (!eligible(player, mob, CROWD) || player.distanceToSqr(mob) > radius * radius || LEASES.containsKey(mob) || CanonExpansionCombatA.mentalLeased(mob) || !mob.hasLineOfSight(victim)) continue;
            LEASES.put(mob, new Lease(player, player.serverLevel(), mob, victim, now + duration));
            mob.setTarget(victim);
            if (mob.getTarget() != victim) { LEASES.remove(mob); continue; }
            cue(player.serverLevel(), mob.position().add(0, mob.getBbHeight() * .7, 0), true);
            CanonVfx.emit(player,CROWD,player.getEyePosition(),mob.position(),CanonVfx.Phase.ACTIVE,duration,applied==0?radius:0);
            applied++;
        }
        if (applied == 0) return false;
        experience(player, CROWD, .03);
        return true;
    }

    /** One grounded, surface-based air jet; server controls the bounded impulse. */
    private static boolean jet(ServerPlayer player) {
        if (!player.onGround() || player.noPhysics || player.isNoGravity() || player.isPassenger() || player.isVehicle()
                || player.isFallFlying() || player.getAbilities().flying || player.isInWaterOrBubble() || player.isInLava()) return false;
        var level = player.serverLevel();
        BlockPos floor = player.blockPosition().below();
        if (!level.hasChunkAt(floor) || level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()) return false;
        Vec3 direction = new Vec3(player.getLookAngle().x, 0, player.getLookAngle().z);
        if (direction.lengthSqr() < 1.0e-8 || !finite(player.getDeltaMovement())) return false;
        direction = direction.normalize();
        Vec3 next = direction.scale(number(JET, "horizontal_speed")).add(0, number(JET, "vertical_speed"), 0);
        if (!level.getWorldBorder().isWithinBounds(player.getBoundingBox().move(next.scale(.5)))
                || !level.noCollision(player, player.getBoundingBox().move(next.scale(.5)))
                || !spend(player, JET, 1, 30)) return false;
        if (!validOwner(player) || !player.onGround() || player.level() != level) return false;
        player.setDeltaMovement(next); player.hasImpulse = true; player.hurtMarked = true;
        cue(level, player.position(), false);
        visual(player,JET,player.position(),player.position().add(next),CanonVfx.Phase.HIT,10);
        experience(player, JET, .01);
        return true;
    }

    /** Launches a real owned block-item drop; it neither removes blocks nor invents impact damage. */
    private static boolean launch(ServerPlayer player) {
        double reach = number(LAUNCH, "reach");
        if (!loadedAlong(player, reach)) return false;
        Vec3 start = player.getEyePosition(), end = start.add(player.getLookAngle().scale(reach));
        var block = player.serverLevel().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double maximum = block.getType() == net.minecraft.world.phys.HitResult.Type.MISS
                ? start.distanceToSqr(end) : start.distanceToSqr(block.getLocation());
        ItemEntity selected = null;
        // A dropped item is a quarter-block target on the ground: accept a crosshair within half a block of it.
        for (ItemEntity item : player.serverLevel().getEntitiesOfClass(ItemEntity.class, new AABB(start, end).inflate(.7),
                e -> eligibleDrop(player, e))) {
            var hit = item.getBoundingBox().inflate(.5).clip(start, end);
            if (hit.isEmpty()) continue;
            double distance = start.distanceToSqr(hit.get());
            if (distance < maximum) { maximum = distance; selected = item; }
        }
        if (selected == null) return false;
        // The item is picked by looking down at it; launch along the aim's heading, never into the ground beneath it.
        Vec3 look = player.getLookAngle();
        Vec3 velocity = new Vec3(look.x, Math.max(look.y, .25), look.z).normalize().scale(number(LAUNCH, "speed")).add(0, number(LAUNCH, "vertical_speed"), 0);
        if (!dropPathSafe(player, selected, velocity) || !spend(player, LAUNCH, 2, 60)) return false;
        if (!validOwner(player) || !AERO.equals(AbilityStorage.get(player).category)
                || !eligibleDrop(player, selected) || !dropPathSafe(player, selected, velocity)) return false;
        selected.setDeltaMovement(velocity); selected.hasImpulse = true; selected.hurtMarked = true;
        cue(player.serverLevel(), selected.position(), false);
        visual(player,LAUNCH,player.getEyePosition(),selected.position(),CanonVfx.Phase.HIT,10);
        experience(player, LAUNCH, .02);
        return true;
    }

    private static boolean eligibleDrop(ServerPlayer player, ItemEntity item) {
        if (item.getClass() != ItemEntity.class || item.getType() != EntityType.ITEM || item.level() != player.level()
                || item.isRemoved() || !item.isAlive() || item.noPhysics || item.isPassenger() || item.isVehicle()
                || item.isInvulnerable() || !MagneticItemProvenance.ownedBy(item, player.getUUID())
                || item.getTarget() != null && !player.getUUID().equals(item.getTarget())
                || item.getItem().isEmpty() || !(item.getItem().getItem() instanceof BlockItem block)
                || block.getBlock() instanceof EntityBlock || item.getItem().getItem() instanceof BundleItem
                || item.getItem().has(DataComponents.CONTAINER) || item.getItem().has(DataComponents.BUNDLE_CONTENTS)
                || item.getItem().has(DataComponents.CONTAINER_LOOT) || item.getItem().has(DataComponents.BLOCK_ENTITY_DATA)
                || !finite(item.position()) || !finite(item.getDeltaMovement())
                || player.serverLevel().getEntity(item.getUUID()) != item
                || player.distanceToSqr(item) > number(LAUNCH, "reach") * number(LAUNCH, "reach")) return false;
        BlockPos pos = item.blockPosition();
        return player.serverLevel().hasChunkAt(pos) && player.serverLevel().mayInteract(player, pos);
    }

    private static boolean dropPathSafe(ServerPlayer player, ItemEntity item, Vec3 velocity) {
        if (!finite(velocity) || velocity.lengthSqr() > 2) return false;
        var world = player.serverLevel(); var swept = item.getBoundingBox().expandTowards(velocity).deflate(1e-7);
        if (!world.getWorldBorder().isWithinBounds(swept)) return false;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(swept.minX, swept.minY, swept.minZ),
                BlockPos.containing(swept.maxX, swept.maxY, swept.maxZ)))
            if (!world.hasChunkAt(pos) || world.isOutsideBuildHeight(pos) || !world.mayInteract(player, pos)) return false;
        return world.noCollision(item, swept);
    }

    /** Concentrated jets from a touched supporting surface; each permitted target is hit once. */
    private static boolean barrage(ServerPlayer player) {
        if (!player.onGround() || player.isInWaterOrBubble() || player.isInLava()) return false;
        var level = player.serverLevel(); var floor = player.blockPosition().below();
        if (!level.hasChunkAt(floor) || level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()) return false;
        Vec3 eye = player.getEyePosition(), direction = player.getLookAngle().normalize();
        var targets = new ArrayList<LivingEntity>(level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(cn.academy.port.core.RangedSkillRanges.fixed(BARRAGE)), target -> barrageCandidate(player, target, level, eye, direction)));
        targets.sort(Comparator.comparingDouble((LivingEntity target) -> player.distanceToSqr(target)).thenComparingInt(Entity::getId));
        if (targets.isEmpty()) return false;
        List<LivingEntity> selected = List.copyOf(targets.subList(0, Math.min(integer(BARRAGE, "target_cap"), targets.size())));
        var emission = emission(player, BARRAGE, 5, 160, selected,
                target -> barrageCandidate(player, target, level, eye, direction));
        if (emission == null) return false;
        int hit = 0;
        for (LivingEntity target : selected) {
            if (barrageCandidate(player, target, level, eye, direction) && emission.attack(target)) {
                hit++;
                if (target.isAlive()) {
                    Vec3 impulse = target.position().subtract(player.position()).multiply(1, 0, 1).normalize().scale(number(BARRAGE, "horizontal_speed")).add(0, number(BARRAGE, "vertical_speed"), 0);
                    if (finite(impulse)) { target.push(impulse.x, impulse.y, impulse.z); target.hurtMarked = true; }
                }
                airLine(player, target.position().add(0, target.getBbHeight() * .5, 0), false);
                visual(player,BARRAGE,eye,target.position(),CanonVfx.Phase.HIT,10);
            }
        }
        if (hit > 0) experience(player, BARRAGE, .03);
        return true;
    }

    private static boolean barrageCandidate(ServerPlayer p, LivingEntity target, ServerLevel level, Vec3 eye, Vec3 direction) {
        if (p.serverLevel() != level || !p.onGround() || p.isInWaterOrBubble() || p.isInLava()
                || !p.getEyePosition().equals(eye) || !p.getLookAngle().normalize().equals(direction)
                || !level.hasChunkAt(p.blockPosition().below())
                || level.getBlockState(p.blockPosition().below()).getCollisionShape(level, p.blockPosition().below()).isEmpty()
                || !attackCandidate(p, target)) return false;
        Vec3 delta = target.getBoundingBox().getCenter().subtract(eye);
        return finite(delta) && delta.lengthSqr() > 1.0e-8 && delta.lengthSqr() <= cn.academy.port.core.RangedSkillRanges.fixed(BARRAGE) * cn.academy.port.core.RangedSkillRanges.fixed(BARRAGE)
                && delta.normalize().dot(direction) >= .7;
    }

    /** A short dark-matter blade forms along the aim ray; impact uses one sealed exact allocation. */
    private static boolean blade(ServerPlayer player) {
        double reach = number(BLADE, "reach");
        LivingEntity target = aimed(player, reach);
        if (!attackCandidate(player, target)) return false;
        ServerLevel level = player.serverLevel(); Vec3 eye = player.getEyePosition(), direction = player.getLookAngle();
        var emission = emission(player, BLADE, 1, 30, List.of(target),
                current -> current == target && aimedCandidate(player, current, level, eye, direction, reach));
        if (emission == null) return false;
        boolean hit = emission.attack(target);
        if (hit) airLine(player, target.position().add(0, target.getBbHeight() * .5, 0), true);
        if (hit) { visual(player,BLADE,eye,target.position(),CanonVfx.Phase.HIT,10);experience(player, BLADE, .02); }
        return true;
    }

    /** Close-range pressure fist only while the paid nitrogen shell is still active. */
    private static boolean punch(ServerPlayer player) {
        Shell shell = SHELLS.get(player);
        if (shell == null || !validShell(shell)) return false;
        double reach = number(PUNCH, "reach");
        LivingEntity target = aimed(player, reach);
        if (!attackCandidate(player, target)) return false;
        ServerLevel level = player.serverLevel(); Vec3 eye = player.getEyePosition(), direction = player.getLookAngle();
        var emission = emission(player, PUNCH, 2, 45, List.of(target),
                current -> current == target && SHELLS.get(player) == shell && validShell(shell)
                        && aimedCandidate(player, current, level, eye, direction, reach));
        if (emission == null) return false;
        boolean hit = emission.attack(target);
        if (hit && target.isAlive()) {
            Vec3 impulse = player.getLookAngle().multiply(1, 0, 1).normalize().scale(number(PUNCH, "horizontal_speed")).add(0, number(PUNCH, "vertical_speed"), 0);
            if (finite(impulse)) { target.push(impulse.x, impulse.y, impulse.z); target.hurtMarked = true; }
        }
        if (hit) airLine(player, target.position().add(0, target.getBbHeight() * .5, 0), false);
        if (hit) { visual(player,PUNCH,eye,target.position(),CanonVfx.Phase.HIT,10);experience(player, PUNCH, .02); }
        return true;
    }

    private static boolean aimedCandidate(ServerPlayer player, LivingEntity target, ServerLevel level,
                                          Vec3 eye, Vec3 direction, double range) {
        return player.serverLevel() == level && player.getEyePosition().equals(eye)
                && player.getLookAngle().equals(direction) && attackCandidate(player, target)
                && aimed(player, range) == target;
    }

    private static CanonEmission.Cast emission(ServerPlayer player, String skill, double multiplier,
                                                int cooldown, List<LivingEntity> targets,
                                                Predicate<LivingEntity> geometry) {
        try {
            double cost = HighPowerGrowth.values(player).arcCost() * multiplier;
            return Double.isFinite(cost) && cost > 0 ? CanonEmission.begin(player, skill, cost, cooldown, targets, geometry) : null;
        } catch (IllegalArgumentException | IllegalStateException invalid) { return null; }
    }
    private static boolean attackCandidate(ServerPlayer player, LivingEntity target) {
        if (target == null || target == player || !target.isAlive() || target.isRemoved() || target.isSpectator()
                || target.level() != player.level() || player.isAlliedTo(target) || target.isAlliedTo(player)
                || !player.hasLineOfSight(target) || HighPowerCombat.unsupportedReason(target).isPresent()) return false;
        return CanonTargetPolicy.hostile(target)
                || target instanceof Player other && AcademyConfig.ATTACK_PLAYERS.get()
                && player.server.isPvpAllowed() && player.canHarmPlayer(other);
    }
    private static void airLine(ServerPlayer player, Vec3 target, boolean darkMatter) {
        Vec3 start = player.getEyePosition();
        for (int step = 1; step <= 12; step++) {
            Vec3 at = start.lerp(target, step / 12.0);
            player.serverLevel().sendParticles(darkMatter ? ParticleTypes.END_ROD : ParticleTypes.CLOUD,
                    at.x, at.y, at.z, 1, .04, .04, .04, 0);
        }
    }

    /** Read-only ownership checks for compatible abilities; no lease is handed to callers. */
    /** Mind Ward shield trait: the attacker loses its target for a moment, held by the same lease Mental Disrupt uses. */
    public static boolean wardDisrupt(ServerPlayer owner, Mob mob, int ticks) {
        if (owner == null || mob == null || ticks <= 0 || LEASES.containsKey(mob) || CanonExpansionCombatA.mentalLeased(mob) || !eligible(owner, mob, DISRUPT)) return false;
        long now = owner.serverLevel().getGameTime();
        LEASES.put(mob, new Lease(owner, owner.serverLevel(), mob, null, now + ticks));
        mob.setTarget(null); mob.getNavigation().stop();
        return true;
    }
    public static boolean mentalTargetLeased(Mob mob) { return mob!=null && LEASES.containsKey(mob); }
    public static boolean ownsNitrogenShell(ServerPlayer p) {
        Shell shell=SHELLS.get(p);return shell!=null && validShell(shell);
    }
    public static boolean ownsDarkWings(ServerPlayer p) {
        Wings wing=WINGS_ACTIVE.get(p);
        return wing!=null && validOwner(p) && p.serverLevel()==wing.level && wing.level.getGameTime()<wing.expires
                && DARK.equals(AbilityStorage.get(p).category) && AbilityStorage.get(p).learned(WINGS)
                && SkillCatalog.enabled(DARK,WINGS) && !p.isCreative() && !p.isSpectator()
                && !p.isInWaterOrBubble() && !p.isInLava() && p.getPersistentData().getBoolean(WINGS_MARK);
    }

    /** A bounded physical shell: it redirects supported arrows using the existing exact shield. */
    private static boolean armor(ServerPlayer player) {
        int duration = integer(ARMOR, "duration_ticks");
        long now = player.serverLevel().getGameTime();
        if (SHELLS.containsKey(player) || player.isInWaterOrBubble() || player.isInLava()
                || HighPowerCombat.shield(player).signum() <= 0 || now < 0 || now > Long.MAX_VALUE - duration
                || !spend(player, ARMOR, 2, 140)) return false;
        if (!validOwner(player) || !NITROGEN.equals(AbilityStorage.get(player).category)
                || player.isInWaterOrBubble() || player.isInLava() || HighPowerCombat.shield(player).signum() <= 0) return false;
        SHELLS.put(player, new Shell(player, player.serverLevel(), now + duration));
        cue(player.serverLevel(), player.position().add(0, 1, 0), false);
        visual(player,ARMOR,player.position(),player.position(),CanonVfx.Phase.START,duration);
        experience(player, ARMOR, .02);
        return true;
    }

    /** Protects nearby actual teammates using the owner's existing paid nitrogen shell. */
    private static boolean dome(ServerPlayer player) {
        int duration = integer(DOME, "duration_ticks");
        Shell shell = SHELLS.get(player); long now = player.serverLevel().getGameTime();
        if (shell == null || !validShell(shell) || DOMES.containsKey(player) || now < 0 || now > Long.MAX_VALUE - duration
                || !spend(player, DOME, 5, 200)) return false;
        if (!validShell(shell) || SHELLS.get(player) != shell) return false;
        DOMES.put(player, new Dome(player, player.serverLevel(), Math.min(shell.expires, now + duration)));
        CanonVfx.emit(player,DOME,player.position(),player.position(),CanonVfx.Phase.START,(int) Math.min(duration, shell.expires - now),number(DOME, "radius"));
        experience(player, DOME, .02);
        return true;
    }

    /** Vanilla flight is granted for a bounded, paid session; saved marker repairs a crash. */
    private static boolean wings(ServerPlayer player) {
        int duration = integer(WINGS, "duration_ticks");
        int boosted = integer(WINGS, "boosted_duration_ticks");
        long now = player.serverLevel().getGameTime();
        if (WINGS_ACTIVE.containsKey(player) || player.getPersistentData().getBoolean(WINGS_MARK)
                || player.isCreative() || player.isSpectator() || player.getAbilities().mayfly
                || player.getAbilities().flying || player.isPassenger() || player.isVehicle()
                || player.isFallFlying() || player.isInWaterOrBubble() || player.isInLava()
                || now < 0 || now > Long.MAX_VALUE - Math.max(duration, boosted) || !spend(player, WINGS, 5, 220)) return false;
        if (!validOwner(player) || !DARK.equals(AbilityStorage.get(player).category)
                || player.getAbilities().mayfly || player.getAbilities().flying || player.isCreative() || player.isSpectator()) return false;
        int wingLife = CanonAuxItems.consume(player, CanonAuxItems.DARK_MATTER_CRYSTAL) ? boosted : duration;
        player.getPersistentData().putBoolean(WINGS_MARK, true);
        WINGS_ACTIVE.put(player, new Wings(player, player.serverLevel(), now + wingLife));
        player.getAbilities().mayfly = true;
        player.onUpdateAbilities();
        wingVisual(player);
        visual(player,WINGS,player.position(),player.position(),CanonVfx.Phase.START,Math.min(200,wingLife));
        experience(player, WINGS, .02);
        return true;
    }

    private static void finishWings(ServerPlayer p, Wings flight, boolean gentleLanding) {
        if (flight != null && !WINGS_ACTIVE.remove(p, flight)) return;
        if (!p.getPersistentData().getBoolean(WINGS_MARK)) return;
        p.getPersistentData().remove(WINGS_MARK);
        if (flight!=null) visual(p,WINGS,p.position(),p.position(),CanonVfx.Phase.END,1);
        // The grant was admitted only when mayfly/flying were both false. Creative and spectator
        // own their native flight permission and must not be rewritten after a game-mode switch.
        if (!p.isCreative() && !p.isSpectator()) {
            p.getAbilities().flying = false; p.getAbilities().mayfly = false; p.onUpdateAbilities();
            if (gentleLanding && p.isAlive() && !p.onGround() && !p.hasEffect(MobEffects.SLOW_FALLING))
                p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, integer(WINGS, "landing_ticks"), 0, false, false, true));
        }
    }

    private static void wingVisual(ServerPlayer player) {
        double yaw = Math.toRadians(player.yBodyRot);
        double rightX = Math.cos(yaw), rightZ = Math.sin(yaw);
        for (int side : new int[]{-1, 1}) for (int i = 1; i <= 5; i++) {
            double offset = side * i * .21;
            player.serverLevel().sendParticles(ParticleTypes.END_ROD,
                    player.getX() + rightX * offset, player.getY() + 1 + i * .16,
                    player.getZ() + rightZ * offset, 1, 0, 0, 0, 0);
        }
    }

    /** Two bounded autonomous shards use a prepaid, closed lifetime damage permit. */
    private static boolean construct(ServerPlayer player) {
        int duration = integer(CONSTRUCT, "duration_ticks");
        long now = player.serverLevel().getGameTime();
        if (CONSTRUCTS.containsKey(player) || HighPowerCombat.shield(player).signum() <= 0
                || now < 0 || now > Long.MAX_VALUE - duration) return false;
        Constructs c = new Constructs(player, player.serverLevel(), now + duration);
        c.permit = CanonEmission.beginConstruct(player, () -> validConstruct(c), target -> validConstructShot(c, target));
        if (c.permit == null) return false;
        if (!validOwner(player) || player.serverLevel() != c.level || c.level.getGameTime() != now
                || !DARK.equals(AbilityStorage.get(player).category) || !AbilityStorage.get(player).learned(CONSTRUCT)
                || !AbilityStorage.get(player).presets.currentContains(CONSTRUCT) || !SkillCatalog.enabled(DARK, CONSTRUCT)
                || !HighPowerResources.ready(player) || CONSTRUCTS.containsKey(player)
                || HighPowerCombat.shield(player).signum() <= 0) { c.permit.cancel(); return false; }
        CONSTRUCTS.put(player, c);
        visual(player,CONSTRUCT,player.position(),player.position(),CanonVfx.Phase.START,duration);
        experience(player, CONSTRUCT, .02);
        return true;
    }

    /** Bounded, server-defined shard location; useful for rendering and native geometry witnesses. */
    public static Vec3 constructPosition(ServerPlayer player, int index) {
        Constructs c = CONSTRUCTS.get(player);
        if (c == null || index < 0 || index > 1 || player.serverLevel() != c.level) return null;
        double angle = c.level.getGameTime() * .15 + index * Math.PI;
        return player.position().add(Math.cos(angle) * 1.35, 1.15 + .15 * Math.sin(angle * 2), Math.sin(angle) * 1.35);
    }

    private static void tickConstruct(Constructs c) {
        ServerPlayer p = c.owner; long now = c.level.getGameTime();
        if (now <= c.lastTick) return;
        c.lastTick = now;
        if (!validConstruct(c) || HighPowerCombat.shield(p).signum() <= 0) {
            stopConstruct(p, c); return;
        }
        Vec3[] shards = {constructPosition(p, 0), constructPosition(p, 1)};
        if (!finite(shards[0]) || !finite(shards[1])) { stopConstruct(p, c); return; }
        if (now % 3 == 0) for (Vec3 at : shards)
            c.level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 4, .16, .16, .16, 0);
        int considered = 0;
        for (AbstractArrow arrow : c.level.getEntitiesOfClass(AbstractArrow.class, p.getBoundingBox().inflate(3),
                a -> (a.getClass() == Arrow.class || a.getClass() == SpectralArrow.class) && a.isAlive()
                        && !a.isRemoved() && a.getOwner() != p && !a.noPhysics && !arrowLeased(a))) {
            if (++considered > 8) break;
            if (arrow.getOwner() instanceof ServerPlayer shooter && (!AcademyConfig.ATTACK_PLAYERS.get()
                    || !p.server.isPvpAllowed() || !p.canHarmPlayer(shooter))) continue;
            Entity shooter = arrow.getOwner();
            Vec3 velocity = arrow.getDeltaMovement(), from = arrow.position();
            if (!finite(velocity) || !finite(from) || velocity.lengthSqr() < 1.0e-8 || velocity.lengthSqr() > 25) continue;
            for (Vec3 shard : shards) {
                var box = new AABB(shard.subtract(.3, .3, .3), shard.add(.3, .3, .3));
                if (box.contains(from)) continue; // Do not bounce the same arrow repeatedly from inside a shard.
                var impact = box.clip(from, from.add(velocity));
                if (impact.isEmpty()) continue;
                Vec3 destination = impact.get().subtract(velocity.normalize().scale(.2));
                var moved = arrow.getBoundingBox().move(destination.subtract(from));
                if (!finite(destination) || !c.level.hasChunkAt(BlockPos.containing(destination))
                        || !c.level.getWorldBorder().isWithinBounds(moved) || !c.level.noCollision(arrow, moved)
                        || !HighPowerCombat.spendShieldFraction(p, new BigDecimal("0.02"))) break;
                if (arrowLeased(arrow) || arrow.isRemoved() || arrow.level() != c.level
                        || !arrow.position().equals(from) || !arrow.getDeltaMovement().equals(velocity)
                        || arrow.getOwner() != shooter || !validConstruct(c)) break;
                arrow.setPos(destination); arrow.setDeltaMovement(velocity.scale(-.6));
                arrow.hasImpulse = true; arrow.hurtMarked = true;
                c.level.sendParticles(ParticleTypes.END_ROD, impact.get().x, impact.get().y, impact.get().z, 8, .15, .15, .15, .01);
                visual(p,CONSTRUCT,p.position(),impact.get(),CanonVfx.Phase.HIT,10);
                break;
            }
        }
        if (validConstruct(c) && HighPowerCombat.shield(p).signum() > 0) attackConstruct(c, shards);
    }

    private static void attackConstruct(Constructs c, Vec3[] shards) {
        long now = c.level.getGameTime();
        if (now < c.nextAttack || c.shot != null || c.permit == null) return;
        var candidates = new ArrayList<LivingEntity>(c.level.getEntitiesOfClass(LivingEntity.class,
                c.owner.getBoundingBox().inflate(number(CONSTRUCT, "radius")), target -> constructTarget(c, target)));
        candidates.sort(Comparator.comparingDouble((LivingEntity t) -> c.owner.distanceToSqr(t)).thenComparingInt(Entity::getId));
        int inspected = 0;
        for (LivingEntity target : candidates) {
            if (++inspected > 32) break;
            for (int i = 0; i < shards.length; i++) {
                ConstructShot shot = new ConstructShot(target, shards[i], target.getBoundingBox().getCenter(), i, now);
                c.shot = shot;
                if (!validConstructShot(c, target)) { c.shot = null; continue; }
                c.nextAttack = now + 20;
                try {
                    if (c.permit.attack(target)) {
                        visual(c.owner,CONSTRUCT,shot.origin,shot.endpoint,CanonVfx.Phase.HIT,10);
                        for (int step = 1; step <= 8; step++) {
                            Vec3 at = shot.origin.lerp(shot.endpoint, step / 8.0);
                            c.level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 1, 0, 0, 0, 0);
                        }
                    }
                } finally { c.shot = null; }
                return;
            }
        }
    }

    private static boolean constructTarget(Constructs c, LivingEntity target) {
        return (CanonTargetPolicy.hostile(target))
                && attackCandidate(c.owner, target) && c.owner.distanceToSqr(target) <= number(CONSTRUCT, "radius") * number(CONSTRUCT, "radius");
    }

    private static boolean validConstructShot(Constructs c, LivingEntity target) {
        ConstructShot shot = c.shot;
        if (shot == null || shot.target != target || shot.tick != c.level.getGameTime() || !validConstruct(c)
                || HighPowerCombat.shield(c.owner).signum() <= 0 || !constructTarget(c, target)
                || !finite(shot.origin) || !finite(shot.endpoint) || !shot.origin.equals(constructPosition(c.owner, shot.shard))
                || shot.origin.distanceToSqr(shot.endpoint) > number(CONSTRUCT, "reach") * number(CONSTRUCT, "reach") || shot.origin.distanceToSqr(shot.endpoint) < 1.0e-8
                || target.getBoundingBox().inflate(.1).clip(shot.origin, shot.endpoint).isEmpty()) return false;
        Vec3 delta = shot.endpoint.subtract(shot.origin);
        int steps = (int) Math.ceil(delta.length() * 2);
        for (int i = 0; i <= steps; i++) {
            BlockPos pos = BlockPos.containing(shot.origin.add(delta.scale((double) i / steps)));
            if (!c.level.hasChunkAt(pos) || c.level.isOutsideBuildHeight(pos)
                    || !c.level.getWorldBorder().isWithinBounds(pos) || !c.level.mayInteract(c.owner, pos)) return false;
        }
        if (c.level.clip(new ClipContext(shot.origin, shot.endpoint, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, c.owner)).getType() != HitResult.Type.MISS) return false;
        int inspected = 0;
        for (LivingEntity other : c.level.getEntitiesOfClass(LivingEntity.class,
                new AABB(shot.origin, shot.endpoint).inflate(1), e -> e != c.owner && e != target && e.isAlive() && !e.isSpectator())) {
            if (++inspected > 32 || other.getBoundingBox().inflate(.1).clip(shot.origin, shot.endpoint).isPresent()) return false;
        }
        return true;
    }

    private static void stopConstruct(ServerPlayer owner, Constructs c) {
        if (c != null && CONSTRUCTS.remove(owner, c)) {
            c.shot = null;if(c.permit!=null)c.permit.cancel();visual(owner,CONSTRUCT,owner.position(),owner.position(),CanonVfx.Phase.END,1);
        }
    }

    private static boolean validConstruct(Constructs c) {
        ServerPlayer p = c.owner;
        return CONSTRUCTS.get(p) == c && validOwner(p) && p.serverLevel() == c.level
                && c.level.getGameTime() < c.expires && DARK.equals(AbilityStorage.get(p).category)
                && AbilityStorage.get(p).learned(CONSTRUCT) && SkillCatalog.enabled(DARK, CONSTRUCT);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void shieldArrow(ProjectileImpactEvent event) {
        if (event.isCanceled() || !(event.getRayTraceResult() instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof ServerPlayer player)
                || !(event.getProjectile() instanceof AbstractArrow arrow)
                || !(arrow.getClass() == Arrow.class || arrow.getClass() == SpectralArrow.class)
                || arrow.getOwner() == player || arrowLeased(arrow)) return;
        ServerPlayer defender = player;
        Shell shell = SHELLS.get(player);
        if (shell == null || !validShell(shell)) {
            defender = null; double best = Double.MAX_VALUE;
            double radius = number(DOME, "radius");
            for (Dome candidate : List.copyOf(DOMES.values())) {
                if (!validDome(candidate) || candidate.level != player.serverLevel() || candidate.owner == player
                        || !candidate.owner.isAlliedTo(player) || !player.isAlliedTo(candidate.owner)
                        || !candidate.owner.hasLineOfSight(player)) continue;
                double distance = candidate.owner.distanceToSqr(player);
                if (distance <= radius * radius && distance < best) { best = distance; defender = candidate.owner; }
            }
            if (defender == null) return;
            shell = SHELLS.get(defender);
        }
        if (arrow.level() != shell.level || arrow.isRemoved()
                || arrow.getOwner() instanceof ServerPlayer other
                && (!AcademyConfig.ATTACK_PLAYERS.get() || !player.server.isPvpAllowed() || !player.canHarmPlayer(other))) return;
        Entity shooter = arrow.getOwner();
        Vec3 velocity = arrow.getDeltaMovement(), from = arrow.position();
        if (!finite(from) || !finite(velocity) || velocity.lengthSqr() < 1.0e-8) return;
        Vec3 outward = velocity.normalize().scale(-.35);
        Vec3 destination = hit.getLocation().add(outward);
        if (!finite(destination) || !shell.level.hasChunkAt(BlockPos.containing(destination))
                || !shell.level.getWorldBorder().isWithinBounds(arrow.getBoundingBox().move(destination.subtract(arrow.position())))
                || !shell.level.noCollision(arrow, arrow.getBoundingBox().move(destination.subtract(arrow.position())))
                || !HighPowerCombat.spendShieldFraction(defender, new BigDecimal("0.04"))) return;
        if (event.isCanceled() || arrowLeased(arrow) || arrow.isRemoved() || arrow.level() != shell.level
                || !arrow.position().equals(from) || !arrow.getDeltaMovement().equals(velocity)
                || arrow.getOwner() != shooter || SHELLS.get(defender) != shell || !validShell(shell)
                || defender != player && (DOMES.get(defender) == null
                || !validDome(DOMES.get(defender)) || !defender.isAlliedTo(player) || !player.isAlliedTo(defender)
                || defender.distanceToSqr(player) > number(DOME, "radius") * number(DOME, "radius") || !defender.hasLineOfSight(player))) return;
        event.setCanceled(true);
        arrow.setPos(destination); arrow.setDeltaMovement(velocity.scale(-.6));
        arrow.hasImpulse = true; arrow.hurtMarked = true;
        cue(shell.level, hit.getLocation(), false);
        visual(defender,defender==player?ARMOR:DOME,defender.position(),hit.getLocation(),CanonVfx.Phase.HIT,10);
    }

    /** Respect every existing native arrow steering controller, including persisted leases. */
    public static boolean arrowLeased(AbstractArrow a) {
        return ElectromasterPrototype.leased(a) || MultiAxisReflectionArrowBridge.leased(a)
                || TrajectoryReturnArrowBridge.leased(a) || ShortInterceptionArrowBridge.leased(a)
                || IronSandLance.leased(a) || MagneticArrowSeparation.leased(a) || MagneticDomainArrows.leased(a)
                || MagneticGroundStep.leased(a) || ElectromasterChargeDecoy.leased(a);
    }

    private static boolean spend(ServerPlayer p, String skill, double multiplier, int cooldown) {
        try {
            double cost = HighPowerGrowth.values(p).arcCost() * multiplier;
            return Double.isFinite(cost) && cost > 0 && HighPowerResources.trySpend(p, skill, cost, cooldown);
        } catch (IllegalArgumentException | IllegalStateException invalid) { return false; }
    }

    private static LivingEntity aimed(ServerPlayer p, double range) {
        if (!loadedAlong(p, range)) return null;
        var hit = ClassicRaytrace.living(p, range, ClipContext.Fluid.NONE);
        return hit instanceof EntityHitResult entity && entity.getEntity() instanceof LivingEntity living ? living : null;
    }

    private static boolean loadedAlong(ServerPlayer p, double range) {
        Vec3 start = p.getEyePosition(), direction = p.getLookAngle();
        for (double d = 0; d <= range; d += 4)
            if (!p.serverLevel().hasChunkAt(BlockPos.containing(start.add(direction.scale(d))))) return false;
        return true;
    }

    private static boolean eligible(ServerPlayer owner, Mob mob, String skill) {
        double reach = number(skill, "reach");
        return mob instanceof Monster && mob.getType().is(CONTROLLABLE) && mob.isAlive() && !mob.isRemoved()
                && !mob.isNoAi() && !mob.isInvulnerable() && mob.level() == owner.level()
                && owner.serverLevel().getEntity(mob.getId()) == mob && !mob.isAlliedTo(owner) && !owner.isAlliedTo(mob)
                && owner.distanceToSqr(mob) <= reach * reach && owner.hasLineOfSight(mob);
    }
    private static boolean validOwner(ServerPlayer p) {
        if (p == null || !p.server.isSameThread() || !p.isAlive() || p.isRemoved() || p.isSpectator() || p.hasDisconnected()) return false;
        var state = AbilityStorage.get(p);
        return state.activated && state.overloadFine && !state.interfering;
    }
    private static boolean validShell(Shell shell) {
        ServerPlayer p = shell.owner;
        if (!validOwner(p) || p.serverLevel() != shell.level || shell.level.getGameTime() >= shell.expires
                || p.isInWaterOrBubble() || p.isInLava() || HighPowerCombat.shield(p).signum() <= 0) return false;
        var state = AbilityStorage.get(p);
        return NITROGEN.equals(state.category) && state.learned(ARMOR) && SkillCatalog.enabled(NITROGEN, ARMOR);
    }
    private static boolean validDome(Dome dome) {
        if (dome == null || dome.level.getGameTime() >= dome.expires || dome.owner.serverLevel() != dome.level) return false;
        Shell shell = SHELLS.get(dome.owner);
        return shell != null && validShell(shell) && AbilityStorage.get(dome.owner).learned(DOME)
                && SkillCatalog.enabled(NITROGEN, DOME);
    }
    private static boolean finite(Vec3 v) {
        return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }
    private static void cue(ServerLevel level, Vec3 at, boolean mental) {
        level.sendParticles(mental ? ParticleTypes.ENCHANT : ParticleTypes.CLOUD,
                at.x, at.y, at.z, mental ? 18 : 14, .3, .25, .3, .02);
    }
    private static void visual(ServerPlayer player,String skill,Vec3 from,Vec3 to,CanonVfx.Phase phase,int duration) {
        CanonVfx.emit(player,skill,from,to,phase,duration);
    }
    private static void experience(ServerPlayer p, String skill, double amount) {
        var state = AbilityStorage.get(p);
        if (state.learned(skill) && SkillCatalog.find(state.category, skill).isPresent()) {
            state.addExperience(skill, amount);
            AbilityStorage.save(p);
        }
    }

    @SubscribeEvent public static void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide()) return;
        Lease lease = LEASES.get(mob);
        if (lease == null) return;
        if (mob.level() != lease.level || lease.level.getGameTime() >= lease.expires
                || !validOwner(lease.owner) || !MENTAL.equals(AbilityStorage.get(lease.owner).category)
                || !eligible(lease.owner, mob, lease.target == null ? DISRUPT : CROWD)
                || lease.target != null && !eligible(lease.owner, lease.target, CROWD)) {
            release(mob, lease); return;
        }
        if (lease.target == null) {
            mob.setTarget(null); mob.getNavigation().stop();
            if (mob.getTarget() != null) release(mob, lease);
        }
        else if (mob.hasLineOfSight(lease.target)) {
            mob.setTarget(lease.target);
            if (mob.getTarget() != lease.target) release(mob, lease);
        }
        else release(mob, lease);
    }
    private static void release(Mob mob, Lease lease) {
        if (!LEASES.remove(mob, lease)) return;
        if (lease.target != null && mob.getTarget() == lease.target) mob.setTarget(null);
        visual(lease.owner,lease.target==null?DISRUPT:CROWD,lease.owner.position(),lease.owner.position(),CanonVfx.Phase.END,1);
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof Mob mob) {
            Lease lease = LEASES.get(mob); if (lease != null) release(mob, lease);
        }
    }
    private static void finishShell(ServerPlayer p) {
        if (SHELLS.remove(p)!=null) visual(p,ARMOR,p.position(),p.position(),CanonVfx.Phase.END,1);
    }
    private static void finishDome(ServerPlayer p) {
        if (DOMES.remove(p)!=null) visual(p,DOME,p.position(),p.position(),CanonVfx.Phase.END,1);
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer owner) {
            finishShell(owner);
            finishDome(owner);
            stopConstruct(owner, CONSTRUCTS.get(owner));
            finishWings(owner, WINGS_ACTIVE.get(owner), false);
            for (Lease lease : List.copyOf(LEASES.values())) if (lease.owner == owner) release(lease.mob, lease);
        }
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getPersistentData().getBoolean(WINGS_MARK))
            finishWings(player, null, false);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            stopConstruct(player, CONSTRUCTS.get(player));
            finishWings(player, WINGS_ACTIVE.get(player), true);
        }
    }
    @SubscribeEvent public static void tickPlayer(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.tickCount % 20 == 0) cn.academy.port.highpower.HighPowerProgression.certifyCanon(player);
        Constructs constructs = CONSTRUCTS.get(player);
        if (constructs != null) tickConstruct(constructs);
        Dome dome = DOMES.get(player);
        if (dome != null) {
            if (!validDome(dome)) finishDome(player);
            else if (dome.level.getGameTime() % 5 == 0)
                dome.level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1, player.getZ(), 8, 1.5, .6, 1.5, .01);
        }
        Wings wings = WINGS_ACTIVE.get(player);
        if (wings != null) {
            var state = AbilityStorage.get(player);
            if (!validOwner(player) || player.serverLevel() != wings.level || wings.level.getGameTime() >= wings.expires
                    || !DARK.equals(state.category) || !state.learned(WINGS) || !SkillCatalog.enabled(DARK, WINGS)
                    || player.isCreative() || player.isSpectator() || player.isInWaterOrBubble() || player.isInLava())
                finishWings(player, wings, true);
            else if (wings.level.getGameTime() % 5 == 0) wingVisual(player);
        }
        Shell shell = SHELLS.get(player);
        if (shell == null) return;
        if (!validShell(shell)) { finishShell(player); return; }
        if (shell.level.getGameTime() % 5 == 0)
            shell.level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1, player.getZ(), 5, .45, .6, .45, .01);
    }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer player) {
            finishShell(player);
            finishDome(player);
            stopConstruct(player, CONSTRUCTS.get(player));
            finishWings(player, WINGS_ACTIVE.get(player), false);
        }
        if (event.getEntity() instanceof ServerPlayer replacement && replacement.getPersistentData().getBoolean(WINGS_MARK))
            finishWings(replacement, null, false);
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        for (Constructs c : List.copyOf(CONSTRUCTS.values())) stopConstruct(c.owner, c);
        for (ServerPlayer p : List.copyOf(DOMES.keySet())) finishDome(p);
        for (ServerPlayer p : List.copyOf(SHELLS.keySet())) finishShell(p);
        for (Wings flight : List.copyOf(WINGS_ACTIVE.values())) finishWings(flight.owner, flight, false);
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { LEASES.clear(); SHELLS.clear(); DOMES.clear(); WINGS_ACTIVE.clear(); CONSTRUCTS.clear(); PERFORMING.clear(); }
}
